package com.example.jwtacces.DTO.debt;

import com.example.jwtacces.DTO.user.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class DebtDTO {
    private Long id;
    private boolean isCreditor;
    private Double amount;
    private String date;
    private String description;
    private Boolean isPaid;
    private UserDTO counterpartyUser;
}
