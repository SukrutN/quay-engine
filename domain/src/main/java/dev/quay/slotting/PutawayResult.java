package dev.quay.slotting;

import java.util.List;
import java.util.Map;

/** One finished putaway run: the directives, the bays they filled, and what the face asked for. */
public record PutawayResult(
        String policy,
        List<PutawayDirective> directives,
        List<DirectiveRefusal> refusals,
        List<ReplenishSignal> signals,
        Map<String, SlotFill> endState,
        PutawayTotals totals) {

    public PutawayResult {
        directives = List.copyOf(directives);
        refusals = List.copyOf(refusals);
        signals = List.copyOf(signals);
        endState = Map.copyOf(endState);
    }
}
