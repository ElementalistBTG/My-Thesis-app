
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * This controller is responsible for inserting new users to the database.
 */
@WebServlet(name = "Addservlet", urlPatterns = {"/add_user"})
public class AddServlet extends HttpServlet {

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  the servlet request.
     * @param response the servlet response.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("Please use the POST method!");
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  the servlet request.
     * @param response the servlet response.
     * @throws ServletException if a servlet-specific error occurs.
     * @throws IOException      if an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = response.getWriter()) {
            String userId = request.getParameter("userId");
            String database_url = request.getParameter("Url");

            if (database_url.isEmpty()) {
                out.println("The url is empty.");
                return;
            }

            Connection conn;
            String url = "jdbc:mysql://localhost:3306/your-db"; //Database -> your-db
            String user = "root";
            String pass = "";
            try {
                Class.forName("com.mysql.jdbc.Driver");
                conn = DriverManager.getConnection(url, user, pass);
                // the mysql insert statement
                String query = " insert into users (userId, Url)"
                        + " values (?, ?)";
                // create the mysql insert preparedstatement
                PreparedStatement preparedStmt = conn.prepareStatement(query);
                preparedStmt.setString(1, userId);
                preparedStmt.setString(2, database_url);
                // execute the preparedstatement
                preparedStmt.executeUpdate();
                //System.out.println("executed correctly");
                conn.close();
                out.println("<html><body><b>Successfully Inserted"
                        + "</b></body></html>");
            } catch (Exception ex) {
                System.out.println("Error" + ex);
            }
        }


    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "This servlet adds new persons to the database.";
    }

}
