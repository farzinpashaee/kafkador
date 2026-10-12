package com.csl.kafkador.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

/** One condition a record must meet. Text comparisons run on the decoded key/value; CONTAINS ignores case. */
@Data
@Accessors(chain = true)
public class RecordFilterDto {

    public enum Field { KEY, VALUE, HEADERS, ANY }

    public enum Operator { CONTAINS, NOT_CONTAINS, EQUALS }

    @NotNull(message = "Filter field is required")
    private Field field;

    @NotNull(message = "Filter operator is required")
    private Operator operator;

    @NotBlank(message = "Filter value is required")
    @Size(max = 1000, message = "Filter value must not exceed 1000 characters")
    private String value;

}
