// =====================================================
// FILE 1: SubscriptionStatus.java
// =====================================================
package com.paycycle.billing_engine.domain.enums;

/**
 * Subscription ka State Machine — ye states hain:
 *
 *  TRIALING  →  ACTIVE  →  PAST_DUE  →  CANCELLED
 *                ↓                          ↑
 *              PAUSED   ──────────────────→ ┘
 *                ↓
 *             EXPIRED
 *
 * Har transition guarded hai — seedha TRIALING se CANCELLED
 * nahi ja sakte bina beech ke states ke.
 */
public enum SubscriptionStatus {
    TRIALING,    // Free trial chal raha hai
    ACTIVE,      // Payment ho rahi hai — healthy state
    PAST_DUE,    // Payment fail hui — retry hogi
    PAUSED,      // Customer ne khud pause kiya
    CANCELLED,   // Band ho gaya
    EXPIRED      // Trial khatam, convert nahi hua
}
