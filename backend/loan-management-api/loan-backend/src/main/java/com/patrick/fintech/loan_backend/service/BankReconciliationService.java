package com.patrick.fintech.loan_backend.service;
import com.patrick.fintech.loan_backend.model.*; import com.patrick.fintech.loan_backend.repository.*; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.io.*; import java.math.*; import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.util.*;
@Service @RequiredArgsConstructor
public class BankReconciliationService{
 private final BankStatementLineRepository statementRepo; private final BankAccountRepository bankRepo; private final JournalLineRepository lineRepo;
 @Transactional public int importCsv(Organization org,Long bankAccountId,InputStream input,String actor)throws IOException{
  BankAccount account=bankRepo.findByIdAndOrganization_Id(bankAccountId,org.getId()).orElseThrow(()->new IllegalArgumentException("Bank account not found")); if(account.getGlAccount()==null||account.getGlAccount().getId()==null)throw new IllegalStateException("Bank account has no GL account"); int count=0; try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String h=r.readLine(); if(h==null)throw new IllegalArgumentException("CSV is empty"); Map<String,Integer> m=headers(h); require(m,"transaction_date");require(m,"amount"); String row; while((row=r.readLine())!=null){if(row.isBlank())continue;List<String> c=parse(row);LocalDate date=LocalDate.parse(val(c,m,"transaction_date"));BigDecimal amount=new BigDecimal(val(c,m,"amount")).setScale(2,RoundingMode.HALF_UP);String ext=opt(c,m,"external_id"); if(ext!=null&&statementRepo.findByOrganization_IdAndBankAccount_IdAndExternalId(org.getId(),bankAccountId,ext).isPresent())continue; statementRepo.save(BankStatementLine.builder().organization(org).bankAccount(account).transactionDate(date).valueDate(parseDate(opt(c,m,"value_date"))).reference(opt(c,m,"reference")).description(opt(c,m,"description")).amount(amount).currency(Optional.ofNullable(opt(c,m,"currency")).orElse("RWF")).externalId(ext).matchedBy(actor).build());count++;}} return count;
 }
 @Transactional
 public ReconciliationResult reconcile(Organization org, Long bankAccountId, LocalDate from,
                                       LocalDate to, String actor) {
     if (from == null || to == null || from.isAfter(to)) {
         throw new IllegalArgumentException("Enter a valid reconciliation date range");
     }

     // Serialize reconciliation runs for this account, including overlapping periods.
     BankAccount account = bankRepo.findByIdAndOrganizationIdForUpdate(bankAccountId, org.getId())
             .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
     if (account.getGlAccount() == null || account.getGlAccount().getId() == null) {
         throw new IllegalStateException("Bank account has no GL account");
     }

     List<BankStatementLine> lines = statementRepo
             .findByOrganization_IdAndBankAccount_IdAndTransactionDateBetweenOrderByTransactionDateAscIdAsc(
                     org.getId(), bankAccountId, from, to);
     Set<Long> usedJournalIds = new HashSet<>();
     Set<Long> duplicateJournalIds = new HashSet<>();
     for (BankStatementLine prior : statementRepo
             .findByOrganization_IdAndBankAccount_IdAndReconciliationStatusOrderByTransactionDateAscIdAsc(
                     org.getId(), bankAccountId, "MATCHED")) {
         Long journalId = prior.getMatchedJournalEntryId();
         if (journalId != null && !usedJournalIds.add(journalId)) {
             duplicateJournalIds.add(journalId);
         }
     }

     Map<Long, JournalEntry> journals = new LinkedHashMap<>();
     Map<Long, BigDecimal> journalAmounts = new LinkedHashMap<>();
     for (JournalLine line : lineRepo.findVisibleLedgerForAccountAndOrganization(
             account.getGlAccount().getId(), org.getId(), ReportingScopeService.includeBusinessOwnerOnly())) {
         JournalEntry journal = line.getJournalEntry();
         if (journal == null || Boolean.TRUE.equals(journal.getReversed())) {
             continue;
         }
         journals.put(journal.getId(), journal);
         journalAmounts.merge(journal.getId(), line.getDebitDecimal().subtract(line.getCreditDecimal()),
                 BigDecimal::add);
     }

     List<JournalEntry> periodJournals = journals.values().stream()
             .filter(journal -> !journal.getEntryDate().isBefore(from) && !journal.getEntryDate().isAfter(to))
             .toList();
     List<Map<String, Object>> unmatched = new ArrayList<>();
     for (BankStatementLine line : lines) {
         if ("MATCHED".equals(line.getReconciliationStatus())) {
             Long matchedId = line.getMatchedJournalEntryId();
             BigDecimal amount = journalAmounts.get(matchedId);
             if (amount == null || amount.compareTo(line.getAmount()) != 0 || duplicateJournalIds.contains(matchedId)) {
                 unmatched.add(unmatchedItem(line, "Existing match needs review"));
             }
             continue;
         }

         List<JournalEntry> candidates = periodJournals.stream()
                 .filter(journal -> !usedJournalIds.contains(journal.getId()))
                 .filter(journal -> journalAmounts.get(journal.getId()).compareTo(line.getAmount()) == 0)
                 .toList();
         List<JournalEntry> referenceMatches = candidates.stream()
                 .filter(journal -> line.getReference() != null && !line.getReference().isBlank()
                         && line.getReference().equalsIgnoreCase(journal.getReference()))
                 .toList();
         List<JournalEntry> matches = referenceMatches.isEmpty() ? candidates.stream()
                 .filter(journal -> line.getTransactionDate().equals(journal.getEntryDate()))
                 .toList() : referenceMatches;
         if (matches.size() != 1) {
             unmatched.add(unmatchedItem(line, matches.isEmpty() ? "No matching journal" : "Multiple matching journals"));
             continue;
         }

         Long journalId = matches.getFirst().getId();
         line.setReconciliationStatus("MATCHED");
         line.setMatchedJournalEntryId(journalId);
         line.setMatchedAt(LocalDateTime.now());
         line.setMatchedBy(actor);
         statementRepo.save(line);
         usedJournalIds.add(journalId);
     }

     BigDecimal statementTotal = lines.stream().map(BankStatementLine::getAmount)
             .reduce(BigDecimal.ZERO, BigDecimal::add);
     BigDecimal ledgerTotal = periodJournals.stream().map(journal -> journalAmounts.get(journal.getId()))
             .reduce(BigDecimal.ZERO, BigDecimal::add);
     return new ReconciliationResult(account.getId(), from, to, lines.size(), lines.size() - unmatched.size(),
             unmatched.size(), statementTotal.setScale(2, RoundingMode.HALF_UP),
             ledgerTotal.setScale(2, RoundingMode.HALF_UP),
             statementTotal.subtract(ledgerTotal).setScale(2, RoundingMode.HALF_UP), unmatched,
             sha256(org.getId() + "|" + bankAccountId + "|" + from + "|" + to + "|" + statementTotal + "|" + ledgerTotal));
 }

 private Map<String, Object> unmatchedItem(BankStatementLine line, String reason) {
     return Map.of("statementLineId", line.getId(), "date", line.getTransactionDate().toString(),
             "amount", line.getAmount(), "reference", Optional.ofNullable(line.getReference()).orElse(""),
             "reason", reason);
 }

 public record ReconciliationResult(Long bankAccountId,LocalDate from,LocalDate to,int statementLines,int matched,int unmatched,BigDecimal statementNet,BigDecimal ledgerNet,BigDecimal difference,List<Map<String,Object>> unmatchedItems,String reportHash){}
 private Map<String,Integer> headers(String h){Map<String,Integer> m=new HashMap<>();List<String> x=parse(h);for(int i=0;i<x.size();i++)m.put(x.get(i).trim().toLowerCase(Locale.ROOT),i);return m;} private void require(Map<String,Integer>m,String k){if(!m.containsKey(k))throw new IllegalArgumentException("Missing CSV column: "+k);} private String val(List<String>c,Map<String,Integer>m,String k){int i=m.get(k);if(i>=c.size())throw new IllegalArgumentException("Missing value for "+k);return c.get(i).trim();} private String opt(List<String>c,Map<String,Integer>m,String k){return !m.containsKey(k)||m.get(k)>=c.size()||c.get(m.get(k)).isBlank()?null:c.get(m.get(k)).trim();} private LocalDate parseDate(String s){return s==null?null:LocalDate.parse(s);} private List<String> parse(String s){List<String>r=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(char c:s.toCharArray()){if(c=='"'){q=!q;continue;}if(c==','&&!q){r.add(b.toString());b.setLength(0);}else b.append(c);}r.add(b.toString());return r;} private String sha256(String s){try{byte[]b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder x=new StringBuilder();for(byte v:b)x.append(String.format("%02x",v));return x.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
