import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * create timer to run every 10 minutes and execute the UpdateDatabase class
 */
@WebListener
public class AppContextListener implements ServletContextListener {
    //starting of tomcat server executes this
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        ServletContext ctx = servletContextEvent.getServletContext();
        ctx.log("Starting up!!!!");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> new UpdateDatabase(ctx), 0, 10, TimeUnit.MINUTES);
    }
    //closing the tomcat server executes this
    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        ServletContext ctx = servletContextEvent.getServletContext();
        ctx.log("Shutting down!");
    }


}
