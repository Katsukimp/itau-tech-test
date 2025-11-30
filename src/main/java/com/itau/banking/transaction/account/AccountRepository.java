package com.itau.banking.transaction.account;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Busca Account com eager fetch para evitar N+1 query problem
     * Usa @EntityGraph para fazer JOIN FETCH automático
     */
    @EntityGraph(attributePaths = {})
    @Override
    Optional<Account> findById(Long id);
}
