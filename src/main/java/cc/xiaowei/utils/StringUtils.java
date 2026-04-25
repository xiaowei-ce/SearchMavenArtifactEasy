package cc.xiaowei.utils;

public class StringUtils {

    public static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static String escapeSolrParam(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public static String truncateBody(String body, int maxLen) {
        if (body == null) return "null";
        if (body.length() <= maxLen) return body;
        return body.substring(0, maxLen) + "... (truncated, total " + body.length() + " chars)";
    }

    /**
     * @param indent indentation prefix for each inner line (e.g. "    " or "  ")
     */
    public static String buildDependencyXml(String groupId, String artifactId, String version, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency>\n");
        sb.append(indent).append("<groupId>").append(escapeXml(groupId)).append("</groupId>\n");
        sb.append(indent).append("<artifactId>").append(escapeXml(artifactId)).append("</artifactId>\n");
        if (version != null && !version.isEmpty()) {
            sb.append(indent).append("<version>").append(escapeXml(version)).append("</version>\n");
        }
        sb.append("</dependency>");
        return sb.toString();
    }
}
