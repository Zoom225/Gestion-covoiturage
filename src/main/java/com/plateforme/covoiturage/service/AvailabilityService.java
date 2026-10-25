package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.IndisponibiliteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
/** Half-open intervals [start,end); future writers must lock the same zone then vehicle. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AvailabilityService {
 private final IndisponibiliteRepository periods;
 public void requireAvailable(Vehicule vehicle,Instant start,Instant end) {
  if(start==null||end==null||!end.isAfter(start))throw new BusinessRuleException("INVALID_INTERVAL");
  if(periods.overlaps(vehicle.getZone().getId(),vehicle.getId(),start,end))
   throw new BusinessRuleException("RESOURCE_UNAVAILABLE");
 }
}
