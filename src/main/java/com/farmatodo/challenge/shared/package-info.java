/**
 * Shared kernel: cross-cutting concerns used by every slice (security, error handling, tracing, web).
 * <p>
 * Declared as an OPEN module so other slices may use its sub-packages (e.g. {@code shared.error}).
 * It must stay small and free of business logic.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.farmatodo.challenge.shared;

import org.springframework.modulith.ApplicationModule;
