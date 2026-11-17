package cl.duoc.backend.controller;

import cl.duoc.backend.service.MonsterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MonsterController {

    private final MonsterService monsterService;

    public MonsterController(MonsterService monsterService) {
        this.monsterService = monsterService;
    }

    @GetMapping("/api/monster/test")
    public Map<String, Object> probarApi() {
        return monsterService.obtenerPoring();
    }

    @GetMapping("/api/monster/random")
    public Map<String, Object> monstruoAleatorio() {
        return monsterService.obtenerMonstruoAleatorio();
    }
}