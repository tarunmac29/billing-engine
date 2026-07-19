package com.paycycle.billing_engine.domain.enums;

public enum InvoiceStatus {
    DRAFT,          // Abhi generate nahi hua
    OPEN,           // Customer ko charge karna hai
    PAID,           // Payment ho gayi
    VOID,           // Cancel ho gaya
    UNCOLLECTIBLE   // Payment nahi hui, write-off
}
