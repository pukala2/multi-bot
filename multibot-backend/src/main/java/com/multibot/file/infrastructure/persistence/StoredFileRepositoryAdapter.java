package com.multibot.file.infrastructure.persistence;

import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.out.StoredFileRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class StoredFileRepositoryAdapter implements StoredFileRepository {

    private final StoredFileJpaRepository jpaRepository;

    StoredFileRepositoryAdapter(StoredFileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StoredFile save(StoredFile file) {
        StoredFileJpaEntity entity = StoredFileEntityMapper.toJpaEntity(file);
        StoredFileJpaEntity saved = jpaRepository.save(entity);
        return StoredFileEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<StoredFile> findById(StoredFileId id) {
        return jpaRepository.findById(id.value())
                .map(StoredFileEntityMapper::toDomain);
    }

    @Override
    public void deleteById(StoredFileId id) {
        jpaRepository.deleteById(id.value());
    }
}
