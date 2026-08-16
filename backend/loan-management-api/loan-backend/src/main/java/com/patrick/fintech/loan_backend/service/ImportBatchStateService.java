package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportBatchStateService {
    private final ImportBatchRepository repository;
    @Transactional public void update(Long id,String status,int processed,int success,int failed,int percent){repository.findById(id).ifPresent(b->{b.setStatus(status);b.setProcessedRows(processed);b.setSuccessCount(success);b.setFailureCount(failed);b.setProgressPercent(percent);repository.save(b);});}
    @Transactional public void failed(Long id,String message){repository.findById(id).ifPresent(b->{b.setStatus("FAILED");b.setErrorMessage(message);b.setProgressPercent(100);repository.save(b);});}
    @Transactional public void errorReport(Long id,String path){repository.findById(id).ifPresent(b->{b.setErrorReportPath(path);repository.save(b);});}
}
