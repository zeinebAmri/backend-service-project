import org.hibernate.cfg.Configuration
import org.hibernate.SessionFactory

/**
 * Creates the SessionFactory from the hibernate.cfg.x
 *
 */
object HibernateUtil {

  private val sessionFactory = buildSessionFactory
  private def buildSessionFactory: SessionFactory = {

    try {
      new Configuration().configure().buildSessionFactory();
    } catch {
      case ex: Throwable =>
        // log the exception
        System.err.println("Initial SessionFactory creation failed." + ex);
        throw new ExceptionInInitializerError(ex);
    }
  }
  def getSessionFactory = sessionFactory

}