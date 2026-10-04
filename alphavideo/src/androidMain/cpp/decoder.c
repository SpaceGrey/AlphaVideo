#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/pixdesc.h>
#include <libswscale/swscale.h>
#include "alpha_metadata.h"

typedef struct {
    AVFormatContext *format;
    AVCodecContext *codec;
    AVFrame *frame;
    AVPacket *packet;
    struct SwsContext *scale;
    int stream, draining, pending, premultiplied, logged;
    int64_t start_pts;
} Decoder;

static enum AVPixelFormat select_alpha_format(AVCodecContext *context,
    const enum AVPixelFormat *formats) {
    for (const enum AVPixelFormat *f = formats; *f != AV_PIX_FMT_NONE; ++f) {
        const AVPixFmtDescriptor *descriptor = av_pix_fmt_desc_get(*f);
        if (descriptor && (descriptor->flags & AV_PIX_FMT_FLAG_ALPHA)) return *f;
    }
    return AV_PIX_FMT_NONE;
}

static void fail(JNIEnv *env, const char *message, int code) {
    char detail[AV_ERROR_MAX_STRING_SIZE], output[512];
    av_strerror(code, detail, sizeof(detail));
    snprintf(output, sizeof(output), "%s: %s", message, detail);
    jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
    (*env)->ThrowNew(env, exception, output);
}

static void close_decoder(Decoder *d) {
    if (!d) return;
    sws_freeContext(d->scale);
    av_packet_free(&d->packet);
    av_frame_free(&d->frame);
    avcodec_free_context(&d->codec);
    avformat_close_input(&d->format);
    free(d);
}

static int next_frame(Decoder *d) {
    while (1) {
        int result = avcodec_receive_frame(d->codec, d->frame);
        if (result != AVERROR(EAGAIN)) return result;
        if (d->draining) return AVERROR_EOF;
        do {
            result = av_read_frame(d->format, d->packet);
            if (result < 0) break;
            if (d->packet->stream_index == d->stream) break;
            av_packet_unref(d->packet);
        } while (1);
        if (result == AVERROR_EOF) {
            d->draining = 1;
            result = avcodec_send_packet(d->codec, NULL);
        } else if (result >= 0) {
            result = avcodec_send_packet(d->codec, d->packet);
            av_packet_unref(d->packet);
        }
        if (result < 0) return result;
    }
}

JNIEXPORT jlong JNICALL Java_dev_alphavideo_HevcAlphaDecoder_open(
    JNIEnv *env, jobject self, jstring path) {
    Decoder *d = calloc(1, sizeof(*d));
    if (!d) { fail(env, "Allocate decoder", AVERROR(ENOMEM)); return 0; }
    const char *filename = (*env)->GetStringUTFChars(env, path, NULL);
    if (!filename) { close_decoder(d); return 0; }
    int result = avformat_open_input(&d->format, filename, NULL, NULL);
    (*env)->ReleaseStringUTFChars(env, path, filename);
    if (result < 0) goto error;
    result = avformat_find_stream_info(d->format, NULL);
    if (result < 0) goto error;
    result = av_find_best_stream(d->format, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    if (result < 0) goto error;
    d->stream = result;
    AVCodecParameters *parameters = d->format->streams[d->stream]->codecpar;
    if (parameters->codec_id != AV_CODEC_ID_HEVC) {
        result = AVERROR_INVALIDDATA;
        goto error;
    }
    d->premultiplied = alpha_mode_from_hvcc(parameters->extradata, parameters->extradata_size);
    if (d->premultiplied < 0) {
        fail(env, "Missing supported Apple HEVC Alpha SEI metadata", AVERROR_INVALIDDATA);
        close_decoder(d);
        return 0;
    }
    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_HEVC);
    d->codec = avcodec_alloc_context3(codec);
    d->frame = av_frame_alloc();
    d->packet = av_packet_alloc();
    if (!d->codec || !d->frame || !d->packet) { result = AVERROR(ENOMEM); goto error; }
    result = avcodec_parameters_to_context(d->codec, parameters);
    if (result < 0) goto error;
    d->codec->thread_count = 2;
    d->codec->get_format = select_alpha_format;
    result = avcodec_open2(d->codec, codec, NULL);
    if (result < 0) goto error;
    result = next_frame(d);
    if (result < 0) goto error;
    const AVPixFmtDescriptor *descriptor = av_pix_fmt_desc_get(d->frame->format);
    if (!descriptor || !(descriptor->flags & AV_PIX_FMT_FLAG_ALPHA)) {
        fail(env, "HEVC decoder did not output an alpha plane", AVERROR_INVALIDDATA);
        close_decoder(d);
        return 0;
    }
    d->pending = 1;
    d->start_pts = d->frame->best_effort_timestamp;
    return (jlong)(intptr_t)d;
error:
    fail(env, "Open HEVC Alpha movie", result);
    close_decoder(d);
    return 0;
}

JNIEXPORT jlongArray JNICALL Java_dev_alphavideo_HevcAlphaDecoder_info(
    JNIEnv *env, jobject self, jlong handle) {
    Decoder *d = (Decoder *)(intptr_t)handle;
    jlong data[] = {d->frame->width, d->frame->height,
        d->format->duration == AV_NOPTS_VALUE ? 0 : d->format->duration / 1000};
    jlongArray array = (*env)->NewLongArray(env, 3);
    if (array) (*env)->SetLongArrayRegion(env, array, 0, 3, data);
    return array;
}

JNIEXPORT jlong JNICALL Java_dev_alphavideo_HevcAlphaDecoder_read(
    JNIEnv *env, jobject self, jlong handle, jobject bitmap) {
    Decoder *d = (Decoder *)(intptr_t)handle;
    if (!d->pending) {
        int result = next_frame(d);
        if (result == AVERROR_EOF) return -1;
        if (result < 0) { fail(env, "Decode HEVC Alpha frame", result); return -1; }
    }
    d->pending = 0;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        info.width != (uint32_t)d->frame->width || info.height != (uint32_t)d->frame->height) {
        fail(env, "Invalid output bitmap", AVERROR(EINVAL)); return -1;
    }
    d->scale = sws_getCachedContext(d->scale, d->frame->width, d->frame->height,
        d->frame->format, d->frame->width, d->frame->height, AV_PIX_FMT_RGBA,
        SWS_BILINEAR, NULL, NULL, NULL);
    if (!d->scale) { fail(env, "Allocate RGBA converter", AVERROR(ENOMEM)); return -1; }
    const int *coefficients = sws_getCoefficients(
        d->frame->colorspace == AVCOL_SPC_BT709 ? SWS_CS_ITU709 : SWS_CS_ITU601);
    sws_setColorspaceDetails(d->scale, coefficients,
        d->frame->color_range == AVCOL_RANGE_JPEG, coefficients, 1, 0, 1 << 16, 1 << 16);
    void *pixels = NULL;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env, "Lock bitmap", AVERROR_EXTERNAL); return -1;
    }
    uint8_t *out[] = {pixels};
    int strides[] = {(int)info.stride};
    int result = sws_scale(d->scale, (const uint8_t *const *)d->frame->data,
        d->frame->linesize, 0, d->frame->height, out, strides);
    int min_alpha = 255, max_alpha = 0, semi = 0;
    for (unsigned y = 0; y < info.height; ++y) {
        uint8_t *row = (uint8_t *)pixels + y * info.stride;
        for (unsigned x = 0; x < info.width; ++x) {
            uint8_t *p = row + x * 4;
            unsigned a = p[3];
            if (a < min_alpha) min_alpha = a;
            if (a > max_alpha) max_alpha = a;
            if (a > 0 && a < 255) ++semi;
            for (int c = 0; c < 3; ++c) {
                // Android Canvas expects premultiplied RGBA; HEVC SEI specifies the input mode.
                p[c] = d->premultiplied ? (p[c] > a ? a : p[c]) : (p[c] * a + 127) / 255;
            }
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    if (result < 0) { fail(env, "Convert HEVC Alpha frame", result); return -1; }
    if (!d->logged) {
        __android_log_print(ANDROID_LOG_INFO, "AlphaVideo",
            "HEVC_ALPHA_RGBA width=%u height=%u alpha_min=%d alpha_max=%d semi_pixels=%d premultiplied=%d",
            info.width, info.height, min_alpha, max_alpha, semi, d->premultiplied);
        d->logged = 1;
    }
    if (d->frame->best_effort_timestamp == AV_NOPTS_VALUE) {
        fail(env, "Missing video frame presentation timestamp", AVERROR_INVALIDDATA); return -1;
    }
    return av_rescale_q(d->frame->best_effort_timestamp - d->start_pts,
        d->format->streams[d->stream]->time_base, (AVRational){1, 1000000});
}

JNIEXPORT void JNICALL Java_dev_alphavideo_HevcAlphaDecoder_restart(
    JNIEnv *env, jobject self, jlong handle) {
    Decoder *d = (Decoder *)(intptr_t)handle;
    int result = av_seek_frame(d->format, d->stream, d->start_pts, AVSEEK_FLAG_BACKWARD);
    if (result < 0) { fail(env, "Loop HEVC Alpha movie", result); return; }
    avcodec_flush_buffers(d->codec);
    av_frame_unref(d->frame);
    av_packet_unref(d->packet);
    d->draining = d->pending = 0;
}

JNIEXPORT void JNICALL Java_dev_alphavideo_HevcAlphaDecoder_close(
    JNIEnv *env, jobject self, jlong handle) {
    close_decoder((Decoder *)(intptr_t)handle);
}
