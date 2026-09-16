package kr.ac.kookmin.familyfitness;

import org.springframework.boot.SpringApplication;

public class TestFamilyfitnessApplication {
    public static void main(String[] args) {
        SpringApplication.from(FamilyfitnessApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
