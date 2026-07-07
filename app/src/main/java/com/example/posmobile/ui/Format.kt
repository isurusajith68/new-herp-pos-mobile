package com.example.posmobile.ui

import java.util.Locale

/** cents -> "1,250.00" */
fun formatCents(cents: Int): String = String.format(Locale.US, "%,.2f", cents / 100.0)
