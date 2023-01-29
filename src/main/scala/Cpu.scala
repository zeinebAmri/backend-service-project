import java.time.LocalDateTime
import javax.persistence._

@Entity
@Table(name = "cpu")
class Cpu {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", unique = true, nullable = false)
  var id: Int = _

  @Column(name = "clientid")
  var clientId: Int = _
  @Column(name = "cpuusage")
  var cpuUsage: Int = _

  @Column(name = "device")
  var device: String = _
  var os: String = _
  var errorMessage: String = _
  var clientVersion: String = _
  @Column(columnDefinition="TEXT")
  var stackTrace: String = _
  @Column(name = "timestamp")
  var timeStamp: LocalDateTime = _


}
