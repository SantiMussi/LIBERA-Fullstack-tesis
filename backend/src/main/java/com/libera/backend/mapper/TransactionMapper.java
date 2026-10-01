package com.libera.backend.mapper;

import com.libera.backend.domain.entity.Transaction;
import com.libera.backend.dto.response.TransactionResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionMapper {

    @Mapping(source = "resalePurchase.id", target = "resalePurchaseId")
    TransactionResponseDTO toDto(Transaction transaction);
}
