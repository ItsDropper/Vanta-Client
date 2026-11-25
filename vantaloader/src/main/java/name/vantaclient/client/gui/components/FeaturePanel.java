package name.vantaclient.client.gui.components;

import name.vantaclient.client.feature.Feature;

import java.util.ArrayList;
import java.util.List;

public class FeaturePanel {

    private final int x;
    private final int y;

    private final List<Feature> features = new ArrayList<>();

    public FeaturePanel(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void addFeature(Feature feature) {
        features.add(feature);
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}