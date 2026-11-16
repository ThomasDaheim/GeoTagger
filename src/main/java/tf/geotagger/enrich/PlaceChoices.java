/*
 * Copyright (c) 2014ff Thomas Feuster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tf.geotagger.enrich;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tf.geo.service.NearbyPlace;
import tf.geo.service.Place;
import tf.geotagger.model.MediaFile;

/**
 * Choosing among several places nearby (plan 5c) - which files get one dialog, and what "Use for all" means for the
 * others.
 */
public final class PlaceChoices {
    private PlaceChoices() {
    }

    /**
     * @param choices files with several places nearby, from a PlaceRun
     * @return the files with the same places nearby (e.g. the files of one grid cell) - one dialog each, in the order
     * of the run
     */
    public static List<List<MediaFile>> groups(Map<MediaFile, List<NearbyPlace>> choices) {
        final Map<List<NearbyPlace>, List<MediaFile>> groups = new LinkedHashMap<>();
        choices.forEach((file, nearby) -> groups.computeIfAbsent(nearby, key -> new ArrayList<>()).add(file));
        return groups.values().stream().map(List::copyOf).toList();
    }

    /**
     * "Use for all": the choice applies to another group only if the chosen city is near it as well - a district of
     * Barcelona never lands on photos from Rome.
     *
     * @param chosen place chosen (and maybe edited) in the dialog
     * @param candidates places near the other group, best answer first
     * @return state, city and sublocation of the choice with country and code of the matching candidate - or the best
     * answer of the other group
     */
    public static Place forOtherGroup(Place chosen, List<NearbyPlace> candidates) {
        if (chosen.city() != null) {
            for (NearbyPlace candidate : candidates) {
                if (chosen.city().equals(candidate.place().city())) {
                    return new Place(candidate.place().countryCode(), candidate.place().country(), chosen.state(), chosen.city(),
                            chosen.sublocation());
                }
            }
        }
        return candidates.get(0).place();
    }
}
