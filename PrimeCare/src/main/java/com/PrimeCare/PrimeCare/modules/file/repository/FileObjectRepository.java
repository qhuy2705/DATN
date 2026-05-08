package com.PrimeCare.PrimeCare.modules.file.repository;

import com.PrimeCare.PrimeCare.modules.file.entity.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {
    Optional<FileObject> findTopByStoragePathOrderByIdDesc(String storagePath);
}
