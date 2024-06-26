package com.example.jwtacces.service;

import com.example.jwtacces.models.userEntity.UserEntity;
import com.example.jwtacces.DTO.debt.BalanceDTO;
import com.example.jwtacces.DTO.debt.CreateDebtDTO;
import com.example.jwtacces.models.debt.Debt;
import com.example.jwtacces.DTO.debt.DebtDTO;
import com.example.jwtacces.repository.debt.DebtRepository;
import com.example.jwtacces.repository.notification.NotificationRepository;
import com.example.jwtacces.service.utils.ServiceUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DebtsService {
    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ServiceUtils serviceUtils;

    private static double getTotalAmount(List<Debt> debts){
        Double totalAmount = 0.0;
        for(Debt debt: debts){
            totalAmount += debt.getAmount();
        }
        return totalAmount;
    }



    public ResponseEntity<?> getBalance(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity creditor = serviceUtils.getUserFromAuthentification(authentication);

        List<Debt> debtsAsDebtor = debtRepository.getCurrentDebtsAsDebtor(creditor);
        List<Debt> debtsAsCreditor = debtRepository.getCurrentDebtsAsCreditor(creditor);

        BalanceDTO balanceDTO = BalanceDTO.builder()
                .owe(getTotalAmount(debtsAsDebtor))
                .owed(getTotalAmount(debtsAsCreditor))
                .build();

        return ResponseEntity.ok().body(balanceDTO);
    }



    public ResponseEntity<?> getHistoricalDebts(@Valid @RequestBody String counterpartyUsername){
        Map<String, Object> httpResponse = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = serviceUtils.getUserFromAuthentification(authentication);
        UserEntity counterpartyUser;

        try{
            counterpartyUser = serviceUtils.getUserFromUsername(counterpartyUsername);
        } catch (UsernameNotFoundException e) {
            httpResponse.put("error","No puedes ver las deudas con ese deudor");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        if(counterpartyUser == null || user.getId() == counterpartyUser.getId()){
            httpResponse.put("error","No puedes ver las deudas con ese deudor");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        else {
            List<Debt> debtsAsCreditor = debtRepository.getDebtByCreditorAndDebtor(user, counterpartyUser);
            List<Debt> debtsAsDebtor = debtRepository.getDebtByCreditorAndDebtor(counterpartyUser, user);
            debtsAsCreditor.addAll(debtsAsDebtor);
            debtsAsCreditor.sort(Comparator.comparing(Debt::getDate).reversed());

            List<DebtDTO> debtDTOlist = new ArrayList<>();
            for(Debt debtAsCreditor: debtsAsCreditor){
                debtDTOlist.add(serviceUtils.convertDebtToDTO(debtAsCreditor, user));
            }
            return ResponseEntity.ok().body(debtDTOlist);
        }
    }



    public ResponseEntity<?> getCurrentDebts(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity creditor = serviceUtils.getUserFromAuthentification(authentication);

        List<Debt> debtsAsCreditor = debtRepository.getDebtByCreditorNotPaid(creditor);
        List<Debt> debtsAsDebtor = debtRepository.getDebtByDebtorNotPaid(creditor);

        debtsAsCreditor.addAll(debtsAsDebtor);
        debtsAsCreditor.sort(Comparator.comparing(Debt::getDate).reversed());

        List<DebtDTO> debtDTOlist = new ArrayList<>();
        for(Debt debtAsCreditor: debtsAsCreditor){
            debtDTOlist.add(serviceUtils.convertDebtToDTO(debtAsCreditor, creditor));
        }
        return ResponseEntity.ok().body(debtDTOlist);
    }

    public ResponseEntity<?> saveDebt(@Valid @RequestBody CreateDebtDTO newDebt){
        Map<String, Object> httpResponse = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity creditor = serviceUtils.getUserFromAuthentification(authentication);
        UserEntity debtor;

        try{
            debtor = serviceUtils.getUserFromUsername(newDebt.getDebtorUsername());
        } catch (UsernameNotFoundException e) {
            httpResponse.put("error","No puedes crear una deuda con ese deudor");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        if(debtor == null || creditor.getId() == debtor.getId()){
            httpResponse.put("error","No puedes crear una deuda con ese deudor");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        else {
            //obtener deudas que tienes con el que va a ser tu deudor para ver si le debes algo y calcular la diferencia
            List<Debt> currentDebts = debtRepository.getDebtByCreditorAndDebtorNotPaid(debtor, creditor);
            for(Debt debt: currentDebts){
                //si el monto de la deuda nueva es menor al que ya tenias pendiente, se le resta a esta ultima el valor de la nueva
                //y la nueva aparecera como ya saldada
                if(newDebt.getAmount() < debt.getAmount()){
                    debt.setAmount(debt.getAmount() - newDebt.getAmount());
                    newDebt.setIsPaid(true);
                    break;
                }
                //si el monto es igual se saldaran las dos deudas
                else if(newDebt.getAmount() - debt.getAmount() == 0.0) {
                    debt.setIsPaid(true);
                    newDebt.setIsPaid(true);
                    break;

                }
                //si el monto de la deuda nueva es mayor al de la que ya tenias pendiente, la deuda pendiente se quedara saldada y
                //el monto de la nueva se restara
                else {
                    debt.setIsPaid(true);
                    newDebt.setAmount(newDebt.getAmount() - debt.getAmount());
                }
                //guardar cambios en bd
                debtRepository.save(debt);
            }
            LocalDateTime currentDateTime = LocalDateTime.now();
            Debt debt = Debt.builder()
                    .creditor(creditor)
                    .debtor(debtor)
                    .amount(newDebt.getAmount())
                    .date(currentDateTime)
                    .description(newDebt.getDescription())
                    .isPaid(newDebt.getIsPaid())
                    .build();
            debtRepository.save(debt);
            httpResponse.put("response","Deuda guardada en la base de datos");

            return ResponseEntity.ok().body(httpResponse);
        }
    }

    public ResponseEntity<?> payOffDebt(Long debtId){
        Map<String, Object> httpResponse = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity creditor = serviceUtils.getUserFromAuthentification(authentication);
        Optional<UserEntity> creditorOfDebt = debtRepository.getCreditorOfDebt(debtId);

        if(creditorOfDebt.isPresent()){
            if(creditorOfDebt.get() == creditor){
                debtRepository.setDebtPaid(debtId);
                //borrar las notificaciones asociadas a esa deuda
                notificationRepository.deleteByDebtId(debtId);
                httpResponse.put("response", "Deuda pagada");
                return ResponseEntity.ok().body(httpResponse);
            } else {
                httpResponse.put("error", "No eres el acreedor de esta deuda");
                return ResponseEntity.badRequest().body(httpResponse);
            }

        } else {
            httpResponse.put("error", "No existe la deuda");
            return ResponseEntity.badRequest().body(httpResponse);
        }
    }
}
