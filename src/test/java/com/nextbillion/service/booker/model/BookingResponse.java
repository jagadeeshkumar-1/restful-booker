package com.nextbillion.service.booker.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BookingResponse {

    private int bookingid;
    private Booking booking;
}
