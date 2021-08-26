package org.owasp.benchmark.testcode;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(value = "/xss-00/BenchmarkTest02743")
public class BenchmarkTest02743 extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("X-XSS-Protection", "0");

        String param = request.getParameter("email");
        if (param == null) param = "";
        Pattern p =
                Pattern.compile(
                        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(param);
        boolean b = m.find();
        String sanitizeParam = goodSanitationForTag(param);

        String htmlRespone = "<html><head></head><body><script>";
        if (param != null && param.trim().length() != 0 && b) {

            htmlRespone += "alert(\"Our reply will be sent to your email: " + sanitizeParam + "\")";
        } else {
            htmlRespone += "alert(\"the provided email is not correct: " + sanitizeParam + "\")";
        }
        htmlRespone += "</script></body></html>";
        response.getWriter().println(htmlRespone);
    }

    protected String goodSanitationForTag(String parameter) {
        parameter = parameter.replaceAll("&", "&amp;");
        parameter = parameter.replaceAll("<", "&lt;");
        parameter = parameter.replaceAll(">", "&gt;");
        return parameter;
    }
}
