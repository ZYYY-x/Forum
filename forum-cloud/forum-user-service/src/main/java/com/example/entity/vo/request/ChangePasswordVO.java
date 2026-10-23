package com.example.entity.vo.request;

import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class ChangePasswordVO {
    @Length(max=20,min = 6)
    String password;
    @Length(max=20,min = 6)
    String new_password;
}
