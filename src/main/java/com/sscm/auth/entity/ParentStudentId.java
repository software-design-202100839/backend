package com.sscm.auth.entity;

import lombok.*;
import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParentStudentId implements Serializable {
    private Long parent;
    private Long student;
}
