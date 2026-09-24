package panrid.space.novelka.server.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import panrid.space.novelka.server.account.AccessService;
import panrid.space.novelka.server.account.Role;
import panrid.space.novelka.server.balance.Balance;
import panrid.space.novelka.server.balance.BalanceService;
import panrid.space.novelka.server.balance.TopUpRequest;

import java.security.Principal;
import java.util.Map;

@RestController
public final class BalanceController {
    private final AccessService access;
    private final BalanceService balances;

    public BalanceController(AccessService access, BalanceService balances) {
        this.access = access;
        this.balances = balances;
    }

    @GetMapping("/api/balance")
    public Balance mine(Principal principal) throws Exception {
        return balances.balance(access.require(principal, Role.READER));
    }

    @GetMapping("/api/accounts/{id}/balance")
    public Map<String, Object> history(Principal principal, @PathVariable String id) throws Exception {
        access.require(principal, Role.OWNER);
        return balances.history(id);
    }

    @PostMapping("/api/accounts/{id}/balance")
    public Balance topUp(Principal principal, @PathVariable String id, @RequestBody TopUpRequest request) throws Exception {
        return balances.topUp(access.require(principal, Role.OWNER), id, request);
    }
}
