package com.risen.incidentboard.web;

import com.risen.incidentboard.repo.SiteRepository;
import com.risen.incidentboard.web.dto.Dtos.SiteView;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteRepository sites;

    public SiteController(SiteRepository sites) { this.sites = sites; }

    @GetMapping
    @Operation(summary = "All sites, for the filter dropdown",
            description = "Every site, including sites with no current alerts -- an "
                    + "operator checking that a site is quiet needs it to still be "
                    + "selectable.")
    public List<SiteView> list() {
        return sites.findAll().stream().map(SiteView::of).toList();
    }
}
