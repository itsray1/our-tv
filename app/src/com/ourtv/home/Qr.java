package com.ourtv.home;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.HashMap;
import java.util.Map;

/** A QR code for the phone page: dark modules on ivory, one pixel per module (scaled up without smoothing). */
final class Qr {
    static Bitmap make(String text) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            int w = m.getWidth(), hh = m.getHeight();
            int[] px = new int[w * hh];
            for (int y = 0; y < hh; y++) for (int x = 0; x < w; x++) px[y * w + x] = m.get(x, y) ? 0xFF0B0F17 : Ui.IVORY;
            return Bitmap.createBitmap(px, w, hh, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }
}
