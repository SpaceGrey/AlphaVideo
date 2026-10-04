/* Apple HEVC Alpha interoperability profile: hvcC prefix-SEI payload type 165. */
#pragma once
#include <stdint.h>
#include <stdlib.h>

static unsigned read_bits(const uint8_t *bytes, unsigned *bit, unsigned count) {
    unsigned result = 0;
    while (count--) {
        result = (result << 1) | ((bytes[*bit / 8] >> (7 - *bit % 8)) & 1);
        ++*bit;
    }
    return result;
}

/* Returns 0 for straight, 1 for premultiplied, -1 for absent/unsupported metadata. */
static int alpha_sei(const uint8_t *nal, int length) {
    if (length < 3 || ((nal[0] >> 1) & 63) != 39) return -1;
    uint8_t *rbsp = malloc((size_t)length);
    if (!rbsp) return -1;
    int size = 0, zeros = 0, mode = -1;
    for (int i = 2; i < length; ++i) {
        if (zeros == 2 && nal[i] == 3) { zeros = 0; continue; }
        rbsp[size++] = nal[i];
        zeros = nal[i] == 0 ? zeros + 1 : 0;
    }
    int p = 0;
    while (p + 2 <= size) {
        unsigned type = 0, payload_size = 0;
        while (p < size && rbsp[p] == 255) { type += 255; ++p; }
        if (p >= size) break;
        type += rbsp[p++];
        while (p < size && rbsp[p] == 255) { payload_size += 255; ++p; }
        if (p >= size) break;
        payload_size += rbsp[p++];
        if (payload_size > (unsigned)(size - p)) break;
        if (type == 165 && payload_size >= 4) {
            unsigned bit = 0;
            const uint8_t *data = rbsp + p;
            unsigned cancel = read_bits(data, &bit, 1);
            unsigned use = read_bits(data, &bit, 3);
            unsigned depth = read_bits(data, &bit, 3);
            unsigned transparent = read_bits(data, &bit, 9);
            unsigned opaque = read_bits(data, &bit, 9);
            unsigned incr = read_bits(data, &bit, 1);
            unsigned clip = read_bits(data, &bit, 1);
            if (!cancel && use <= 1 && depth == 0 && transparent == 0 &&
                opaque == 255 && !incr && !clip) mode = (int)use;
            break;
        }
        p += (int)payload_size;
    }
    free(rbsp);
    return mode;
}

static int alpha_mode_from_hvcc(const uint8_t *data, int size) {
    if (size < 23 || data[0] != 1) return -1;
    int p = 23;
    for (int array = 0; array < data[22]; ++array) {
        if (p + 3 > size) return -1;
        ++p;
        unsigned count = ((unsigned)data[p] << 8) | data[p + 1];
        p += 2;
        for (unsigned i = 0; i < count; ++i) {
            if (p + 2 > size) return -1;
            unsigned length = ((unsigned)data[p] << 8) | data[p + 1];
            p += 2;
            if (length > (unsigned)(size - p)) return -1;
            int mode = alpha_sei(data + p, (int)length);
            if (mode >= 0) return mode;
            p += (int)length;
        }
    }
    return -1;
}
