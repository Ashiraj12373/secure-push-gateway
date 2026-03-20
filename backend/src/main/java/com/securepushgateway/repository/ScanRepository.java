package com.securepushgateway.repository;

import com.securepushgateway.model.Scan;
import com.securepushgateway.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScanRepository extends JpaRepository<Scan, Long> {
    List<Scan> findByDeveloperOrderByScannedAtDesc(User developer);
    List<Scan> findByDeveloperOrderByScannedAtAsc(User developer);
    List<Scan> findAllByOrderByScannedAtDesc();

    @Query("SELECT s FROM Scan s WHERE s.developer = :developer AND s.status = 'PASS' ORDER BY s.scannedAt DESC")
    List<Scan> findPassScansByDeveloper(@Param("developer") User developer);

    @Query("SELECT COUNT(s) FROM Scan s WHERE s.developer = :dev AND s.status = 'FAIL'")
    long countFailsByDeveloper(@Param("dev") User dev);
}
