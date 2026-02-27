package io.benwiegand.projection.geargrinder.pm;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static io.benwiegand.projection.geargrinder.pm.CarManifestConstants.CAR_META_DATA_NAME;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public enum CarFeature {
    MEDIA,
    NOTIFICATION,
    TEMPLATE,
    PROJECTION,
    SERVICE;

    private static final String TAG = CarFeature.class.getSimpleName();

    private static final String XML_TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String XML_TAG_USES = "uses";
    private static final String XML_ATTRIBUTE_NAME = "name";

    public static CarFeature fromXmlAttribute(String value) {
        return switch (value) {
            case "media" -> MEDIA;
            case "notification" -> NOTIFICATION;
            case "template" -> TEMPLATE;
            case "projection" -> PROJECTION;
            case "service" -> SERVICE;
            default -> null;
        };
    }

    public static Set<CarFeature> getFeaturesForPackage(PackageManager pm, PackageInfo pkg) {
        Set<CarFeature> features = new HashSet<>();

        ApplicationInfo app = pkg.applicationInfo;
        if (app == null) return Set.of();

        try (XmlResourceParser xml = app.loadXmlMetaData(pm, CAR_META_DATA_NAME)) {
            if (xml == null) return Set.of();

            while (xml.next() != END_DOCUMENT) {
                if (xml.getEventType() != START_TAG) continue;
                if (!XML_TAG_AUTOMOTIVE_APP.equals(xml.getName())) continue;

                int automotiveAppDepth = xml.getDepth();
                do {
                    if (xml.next() != START_TAG) continue;

                    if (XML_TAG_USES.equals(xml.getName())) {
                        CarFeature feat = fromXmlAttribute(xml.getAttributeValue(null, XML_ATTRIBUTE_NAME));
                        if (feat == null) continue;
                        features.add(feat);
                    } else {
                        // skip element
                        int depth = xml.getDepth();
                        do { xml.next(); } while (xml.getDepth() > depth);
                    }
                } while (xml.getDepth() > automotiveAppDepth);

            }

        } catch (Throwable e) {
            Log.e(TAG, "failed to parse car app metadata xml", e);
        }

        return Set.copyOf(features);
    }

}
