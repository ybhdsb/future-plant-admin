package cn.geek51.dao;

import cn.geek51.domain.DatasetLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetLibraryRepository extends JpaRepository<DatasetLibrary, Long> {
    List<DatasetLibrary> findByDatasetType(String datasetType);

    List<DatasetLibrary> findByNameContainingIgnoreCaseOrOwnerNameContainingIgnoreCase(String name, String ownerName);

    List<DatasetLibrary> findByFamilyKeyOrderByCreatedTimeAsc(String familyKey);

    DatasetLibrary findFirstByFlKey(String flKey);

    DatasetLibrary findFirstByName(String name);

    boolean existsByFlKey(String flKey);

    boolean existsByName(String name);
}
