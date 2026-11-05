package org.archvow.idea;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.17.0: the list's own line under the views (the same as the VS Code page's listFilter): small words, never
 * buttons like the views - "Show All · Open 2 · To review 1" - and, when the view has nothing open left, where
 * the work is ("Decisions: all done · Safety: 2 open ›"). Decisions (0), Safety (2) and Rules (4) carry origins;
 * Rules have nothing to answer, so no "Open" there. "To review" shows only when the AI added something here.
 */
final class ArchvowListFilter {
    private ArchvowListFilter() {}

    /** The views that have the line. */
    static boolean applies(int view) {
        return view == 0 || view == 2 || view == 4;
    }

    /** The words, in order: {id, English, French, count}. */
    static List<String[]> words(int view, int open, int review) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[] {"all", "All", "Tout", "0"});
        if (view != 4) out.add(new String[] {"open", "Open", "Ouverts", Integer.toString(Math.max(open, 0))});
        if (review > 0) out.add(new String[] {"review", "To review", "À relire", Integer.toString(review)});
        return out;
    }

    /** The filter still makes sense: "To review" with nothing to review, or "Open" with nothing open, goes back to "All". */
    static String normalized(String filter, int view, int open, int review) {
        if ("review".equals(filter) && review <= 0) return "all";
        if ("open".equals(filter) && (open <= 0 || view == 4)) return "all";
        return filter == null || filter.isBlank() ? "all" : filter;
    }

    /**
     * When this view is done (items, none open): the view that has open items next (0..5 in order, then
     * Other = 7), or -1 when none; -2 when the line says nothing (something is open here, or nothing at all).
     */
    static int doneThenGoTo(int view, int[] pending, int[] total, int otherPending) {
        if (view == 4 || view < 0 || view >= pending.length || pending[view] > 0 || total[view] <= 0) return -2;
        for (int i = 0; i < pending.length; i++) if (i != view && pending[i] > 0) return i;
        return otherPending > 0 ? 7 : -1;
    }
}
