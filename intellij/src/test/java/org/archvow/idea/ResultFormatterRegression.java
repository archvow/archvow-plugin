package org.archvow.idea;

public final class ResultFormatterRegression {
    public static void main(String[] args) {
        String empty = ArchvowResultFormatter.render("next",0,"",false,"python -m archvow next",false,false);
        if (!empty.contains("No output") || !empty.contains("Completed"))
            throw new AssertionError("Empty successful result missing friendly summary");
        String failure = ArchvowResultFormatter.render("show",1,"No module named archvow",false,"python -m archvow show",true,true);
        if (!failure.contains("n&#39;est pas installé") || !failure.contains("Échec"))
            throw new AssertionError("Module missing guidance lost");
        String escaped = ArchvowResultFormatter.render("check",2,"<bad> & unsafe",false,"python -m archvow check",false,false);
        if (!escaped.contains("&lt;bad&gt; &amp; unsafe"))
            throw new AssertionError("Unsanitized output");
        System.out.println("OK: 3 result scenarios");
    }
}
