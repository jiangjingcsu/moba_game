package com.moba.battleserver.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

@Slf4j
public class DataCompressor {
    private static final int COMPRESSION_LEVEL = Deflater.BEST_SPEED;

    public static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        try {
            Deflater deflater = new Deflater(COMPRESSION_LEVEL);
            deflater.setInput(data);
            deflater.finish();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[1024];

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            deflater.end();
            byte[] compressed = outputStream.toByteArray();
            outputStream.close();

            return compressed;
        } catch (IOException e) {
            log.error("Compression failed", e);
            return data;
        }
    }

    public static byte[] decompress(byte[] compressedData) {
        if (compressedData == null || compressedData.length == 0) {
            return compressedData;
        }

        try {
            Inflater inflater = new Inflater();
            inflater.setInput(compressedData);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(compressedData.length);
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            inflater.end();
            byte[] decompressed = outputStream.toByteArray();
            outputStream.close();

            return decompressed;
        } catch (Exception e) {
            log.error("Decompression failed", e);
            return compressedData;
        }
    }

    public static float getCompressionRatio(byte[] original, byte[] compressed) {
        if (original == null || original.length == 0) {
            return 0;
        }
        return (1.0f - (float) compressed.length / original.length) * 100;
    }
}
