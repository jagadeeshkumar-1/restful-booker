package com.nextbillion.service.booker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDates {

    private String checkin;
    private String checkout;
}
