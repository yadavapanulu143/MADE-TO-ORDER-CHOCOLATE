package com.choco.identity.adapter.in.web;

import com.choco.identity.application.command.CreateAddressCommand;
import com.choco.identity.application.command.UpdateAddressCommand;
import com.choco.identity.application.dto.AddressView;
import com.choco.identity.application.service.AddressService;
import com.choco.security.principal.AuthenticatedUser;
import com.choco.security.principal.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's delivery addresses.
 *
 * <p>The owning customer id is always taken from the authenticated token, never from the path or the
 * body. A customer therefore has no way to name another account's addresses, and an administrator
 * acting on a customer's behalf does so through the admin surface rather than by borrowing this one.
 *
 * <p>Paths are nested under {@code /me} so the absence of a customer id in the URL is obvious to a
 * reader; there is no id here to tamper with.
 */
@RestController
@RequestMapping("/api/v1/customers/me/addresses")
@Tag(name = "Addresses", description = "A customer's own delivery addresses")
class AddressController {

    private final AddressService addressService;
    private final CurrentUserProvider currentUserProvider;

    AddressController(AddressService addressService, CurrentUserProvider currentUserProvider) {
        this.addressService = addressService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "List the signed-in customer's addresses")
    ResponseEntity<List<AddressView>> list() {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(addressService.list(caller.userId()));
    }

    @GetMapping("/{addressId}")
    @Operation(summary = "Read one of the signed-in customer's addresses")
    ResponseEntity<AddressView> get(@PathVariable UUID addressId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(addressService.get(caller.userId(), addressId));
    }

    @PostMapping
    @Operation(summary = "Add a delivery address")
    ResponseEntity<AddressView> create(@Valid @RequestBody AddressRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        AddressView created = addressService.add(caller.userId(), toCreateCommand(request));
        return ResponseEntity.created(URI.create("/api/v1/customers/me/addresses/" + created.id()))
                .body(created);
    }

    @PutMapping("/{addressId}")
    @Operation(summary = "Update a delivery address")
    ResponseEntity<AddressView> update(@PathVariable UUID addressId,
                                       @Valid @RequestBody AddressRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(addressService.update(caller.userId(), addressId,
                new UpdateAddressCommand(request.recipientName(), request.mobile(), request.line1(),
                        request.line2(), request.city(), request.state(), request.pincode(),
                        request.deliveryInstructions())));
    }

    @PostMapping("/{addressId}/default")
    @Operation(summary = "Make an address the default for checkout")
    ResponseEntity<AddressView> makeDefault(@PathVariable UUID addressId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        return ResponseEntity.ok(addressService.makeDefault(caller.userId(), addressId));
    }

    @DeleteMapping("/{addressId}")
    @Operation(summary = "Delete a delivery address")
    ResponseEntity<Void> delete(@PathVariable UUID addressId) {
        AuthenticatedUser caller = currentUserProvider.requireUser();
        addressService.delete(caller.userId(), addressId);
        return ResponseEntity.noContent().build();
    }

    private static CreateAddressCommand toCreateCommand(AddressRequest request) {
        return new CreateAddressCommand(request.recipientName(), request.mobile(), request.line1(),
                request.line2(), request.city(), request.state(), request.pincode(),
                request.deliveryInstructions(), request.makeDefault());
    }
}
