package com.newtube.mobile.ui.playback;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selectable description text with ordinary web links and seekable chapter timestamps. */
final class DescriptionText {
    private static final Pattern TIME = Pattern.compile("(?<![\\p{L}\\p{N}:/])(?:(\\d{1,3}):)?([0-5]?\\d):([0-5]\\d)(?![\\d:])");
    private DescriptionText() {}

    static void bind(TextView view, CharSequence value, LongConsumer seek) {
        SpannableString text = new SpannableString(value == null ? "" : value);
        Linkify.addLinks(text, Linkify.WEB_URLS);
        Matcher timestamps = TIME.matcher(text);
        while (timestamps.find()) {
            if (text.getSpans(timestamps.start(), timestamps.end(), URLSpan.class).length > 0) continue;
            long hours = timestamps.group(1) == null ? 0 : Long.parseLong(timestamps.group(1));
            long millis = (hours * 3600 + Long.parseLong(timestamps.group(2)) * 60
                    + Long.parseLong(timestamps.group(3))) * 1000;
            text.setSpan(new ClickableSpan() {
                @Override public void onClick(@NonNull View widget) { seek.accept(millis); }
            }, timestamps.start(), timestamps.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setAutoLinkMask(0);
        view.setText(text);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
