package com.example.jwtacces.repository.contact;

import com.example.jwtacces.models.contact.RequestContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface ContactRequestRepository extends JpaRepository<RequestContact,Integer> {
    //buscar los request que tiene el usuario pendientes
    @Query("SELECT rc FROM RequestContact rc WHERE rc.userId = :userId and rc.accept = false")
    Optional<Set<RequestContact>> findRequestContactByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(rc) > 0 FROM RequestContact rc WHERE rc.userId = :userId and rc.userRequestId = :userRequestId")
    boolean requestAlreadyExist(@Param("userId") Long userId, @Param("userRequestId") Long userRequestId);

    @Modifying
    @Query("UPDATE RequestContact rc SET rc.accept = true WHERE rc.userId = :userId and rc.userRequestId = :userRequestId")
    int validateRequest(@Param("userId") Long userId, @Param("userRequestId") Long userRequestId);

}
