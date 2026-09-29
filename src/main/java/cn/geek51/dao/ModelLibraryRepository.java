package cn.geek51.dao;

import cn.geek51.domain.ModelLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelLibraryRepository extends JpaRepository<ModelLibrary, Long> {
    List<ModelLibrary> findByNameContainingIgnoreCaseOrOwnerNameContainingIgnoreCase(String name, String ownerName);

    List<ModelLibrary> findByFamilyKeyOrderByCreatedTimeAsc(String familyKey);

    List<ModelLibrary> findBySourceDatasetIdOrderByCreatedTimeAsc(Long sourceDatasetId);

    ModelLibrary findFirstByFlKey(String flKey);

    ModelLibrary findFirstByNameAndVersion(String name, String version);

    boolean existsByFlKey(String flKey);

    boolean existsByName(String name);
}
