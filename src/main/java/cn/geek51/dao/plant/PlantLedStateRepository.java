package cn.geek51.dao.plant;

import cn.geek51.domain.plant.PlantLedState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantLedStateRepository extends JpaRepository<PlantLedState, Long> {
    List<PlantLedState> findByRackKeyOrderByBusAddressAscChannelAsc(String rackKey);

    Optional<PlantLedState> findByRackKeyAndBusAddressAndChannel(String rackKey, String busAddress, Integer channel);
}
