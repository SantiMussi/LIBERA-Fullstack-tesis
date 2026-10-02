package com.libera.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Motivo del rechazo de una publicación; el vendedor lo ve en "Mi cuenta". */
@Data
public class ListingReviewRequestDTO {

    @NotBlank(message = "A reason is required to reject a listing")
    @Size(max = 500)
    private String note;
}
