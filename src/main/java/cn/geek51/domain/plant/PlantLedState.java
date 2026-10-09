package cn.geek51.domain.plant;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "plant_led_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_led_state", columnNames = {"rack_key", "bus_address", "channel"})
}, indexes = {
        @Index(name = "idx_led_state_rack", columnList = "rack_key,updated_at")
})
public class PlantLedState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rack_key", length = 64, nullable = false)
    private String rackKey;

    @Column(name = "bus_address", length = 8, nullable = false)
    private String busAddress;

    @Column(name = "channel", nullable = false)
    private Integer channel;

    @Column(name = "spectrum", length = 32)
    private String spectrum;

    @Column(name = "level", nullable = false)
    private Integer level = 0;

    @Column(name = "online")
    private Boolean online = true;

    @Column(name = "source", length = 32)
    private String source;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", columnDefinition = "DATETIME(3)")
    private Date updatedAt;
}
