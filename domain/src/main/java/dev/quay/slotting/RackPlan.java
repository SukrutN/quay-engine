package dev.quay.slotting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Expands a rectangular rack into slots. List order is the index order: the first aisle is
 * index 0, the first bay is the bay nearest the dock mouth.
 */
public record RackPlan(
        List<String> aisles,
        List<Integer> bays,
        List<Integer> levels,
        Set<Integer> goldenLevels,
        int floorLevel,
        Set<Integer> wideBays,
        double wideMultiplier,
        Map<Integer, Double> cubeLitersByLevel,
        double dockToFirstAisleMeters,
        double aislePitchMeters,
        double mouthToFirstBayMeters,
        double bayPitchMeters) {

    public RackPlan {
        aisles = List.copyOf(aisles);
        bays = List.copyOf(bays);
        levels = List.copyOf(levels);
        goldenLevels = Set.copyOf(goldenLevels);
        wideBays = Set.copyOf(wideBays);
        cubeLitersByLevel = Map.copyOf(cubeLitersByLevel);
    }

    public List<Slot> expand() {
        List<String> problems = new ArrayList<>();
        if (aisles.isEmpty()) {
            problems.add("The rack needs at least one aisle.");
        }
        if (bays.isEmpty()) {
            problems.add("The rack needs at least one bay.");
        }
        if (levels.isEmpty()) {
            problems.add("The rack needs at least one level.");
        }
        if (!Double.isFinite(wideMultiplier) || wideMultiplier <= 0) {
            problems.add("wideMultiplier must be a positive number.");
        }
        if (!travelFinite()) {
            problems.add("Rack travel distances must be finite numbers, and pitches must be zero or greater.");
        }
        if (!levels.isEmpty() && !levels.contains(floorLevel)) {
            problems.add("floorLevel " + floorLevel + " is not one of the rack levels.");
        }
        for (int golden : goldenLevels) {
            if (!levels.contains(golden)) {
                problems.add("Golden level " + golden + " is not one of the rack levels.");
            }
        }
        for (int level : levels) {
            Double cube = cubeLitersByLevel.get(level);
            if (cube == null || !Double.isFinite(cube) || cube <= 0) {
                problems.add("Level " + level + " needs a positive cubeLitersByLevel value.");
            }
        }
        if (new LinkedHashSet<>(aisles).size() != aisles.size()) {
            problems.add("Aisle names must be unique.");
        }
        if (new LinkedHashSet<>(bays).size() != bays.size()) {
            problems.add("Bay numbers must be unique.");
        }
        if (!problems.isEmpty()) {
            throw new FaceRejection(problems);
        }

        List<Slot> slots = new ArrayList<>();
        for (int aisleIndex = 0; aisleIndex < aisles.size(); aisleIndex++) {
            String aisle = aisles.get(aisleIndex);
            for (int bayIndex = 0; bayIndex < bays.size(); bayIndex++) {
                int bay = bays.get(bayIndex);
                boolean wide = wideBays.contains(bay);
                for (int level : levels) {
                    double base = cubeLitersByLevel.get(level);
                    double cube = Math.round(base * (wide ? wideMultiplier : 1.0));
                    Ergonomics ergonomics = ergonomicsFor(level);
                    double across = dockToFirstAisleMeters + aisleIndex * aislePitchMeters;
                    double into = mouthToFirstBayMeters + bayIndex * bayPitchMeters;
                    double returns = 2.0 * (across + into);
                    String id = aisle + "-" + String.format("%02d", bay) + "-L" + level;
                    slots.add(new Slot(
                            id,
                            aisle,
                            aisleIndex,
                            bay,
                            bayIndex,
                            level,
                            cube,
                            returns,
                            ergonomics,
                            Optional.empty()));
                }
            }
        }
        return List.copyOf(slots);
    }

    private Ergonomics ergonomicsFor(int level) {
        if (goldenLevels.contains(level)) {
            return Ergonomics.GOLDEN;
        }
        if (level == floorLevel) {
            return Ergonomics.FLOOR;
        }
        return Ergonomics.BEAM;
    }

    private boolean travelFinite() {
        return finiteNonNegative(dockToFirstAisleMeters)
                && finiteNonNegative(aislePitchMeters)
                && finiteNonNegative(mouthToFirstBayMeters)
                && finiteNonNegative(bayPitchMeters);
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0;
    }
}
