package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.util.StreamingLedgerFileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncLegacyImportService {
    private static final long MAX_ROWS=100_000;
    @Value("${app.import.staging-dir:${java.io.tmpdir}/loansaas-imports}") private String stagingDir;
    private final ImportBatchRepository batchRepo;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final LegacyLoanImportRowService rowService;
    private final AuditService auditService;
    private final ImportBatchStateService stateService;

    @Transactional
    public ImportBatch stage(Path stagedFile,String fileName,Long organizationId,Long userId,long fileSize){
        Organization org=organizationRepository.findById(organizationId).orElseThrow(()->new IllegalArgumentException("Organization not found"));
        User user=userRepository.findById(userId).orElseThrow(()->new IllegalArgumentException("User not found"));
        ImportBatch b=ImportBatch.builder().organization(org).importedBy(user).fileName(fileName).totalRows(0).successCount(0).failureCount(0).status("QUEUED").build();
        b.setStagedFilePath(stagedFile.toAbsolutePath().toString()); b.setFileSize(fileSize); b.setProgressPercent(0); b.setProcessedRows(0);
        return batchRepo.save(b);
    }

    @Async("loansaasAsyncExecutor")
    public CompletableFuture<Void> process(Long batchId){
        try{ doProcess(batchId); return CompletableFuture.completedFuture(null); }
        catch(Exception e){ log.error("Async import failed batchId={}",batchId,e); stateService.failed(batchId,e.getMessage()==null?"Import failed":e.getMessage()); return CompletableFuture.failedFuture(e); }
    }

    protected void doProcess(Long batchId)throws Exception{
        ImportBatch batch=batchRepo.findDetailedById(batchId).orElseThrow();
        Organization org=batch.getOrganization();
        Path file=Paths.get(batch.getStagedFilePath());
        Path errors=Path.of(batch.getStagedFilePath()+".errors.csv");
        AtomicInteger processed=new AtomicInteger(); AtomicInteger success=new AtomicInteger(); AtomicInteger failed=new AtomicInteger();
        Map<String,com.patrick.fintech.loan_backend.model.Borrower> sessionBorrowers=new HashMap<>();
        stateService.update(batchId,"PROCESSING",0,0,0,0);
        Files.writeString(errors,"row,error\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        stateService.errorReport(batchId,errors.toAbsolutePath().toString());
        try(InputStream in=Files.newInputStream(file,StandardOpenOption.READ)){
            StreamingLedgerFileParser.stream(batch.getFileName(),in,MAX_ROWS,(rowNumber,row)->{
                var result=rowService.importRow(row,(int)rowNumber,org,batchId,true,sessionBorrowers);
                if(result!=null&&result.isSuccess()) success.incrementAndGet(); else { failed.incrementAndGet(); String error=result==null?"Unknown import error":String.valueOf(result.getError()); Files.writeString(errors,rowNumber+",\""+error.replace("\"","\"\"")+"\"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
                int p=processed.incrementAndGet(); if(p % 100 == 0){ int percent=Math.min(99,Math.max(0,p/100)); stateService.update(batchId,"PROCESSING",p,success.get(),failed.get(),percent); }
            });
        } finally { Files.deleteIfExists(file); }
        String status=failed.get()==0?"COMPLETED":success.get()==0?"FAILED":"PARTIAL";
        stateService.update(batchId,status,processed.get(),success.get(),failed.get(),100);
        auditService.log(org,batch.getImportedBy(),"LEGACY_LOANS_IMPORTED","IMPORT_BATCH",String.valueOf(batchId),"Imported "+success.get()+"/"+processed.get()+" rows. Status: "+status);
    }

    @Scheduled(cron="0 30 3 * * *")
    public void cleanupStagingFiles(){
        try{
            Path root=Path.of(stagingDir); if(!Files.isDirectory(root)) return;
            try(var files=Files.list(root)){ files.filter(Files::isRegularFile).filter(p->{try{return Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-java.util.concurrent.TimeUnit.DAYS.toMillis(7);}catch(Exception e){return false;}}).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception e){log.warn("Could not delete stale import staging file {}",p,e);}}); }
        }catch(Exception e){log.warn("Import staging cleanup failed",e);}
    }

}
