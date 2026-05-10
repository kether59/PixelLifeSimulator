package com.kether.pixellife.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriétés de configuration pour le lancement du frontend depuis le backend.
 *
 * Ces propriétés sont bindées depuis application.properties ou application.yml
 * grâce à l'annotation @ConfigurationProperties. Elles permettent de configurer
 * le comportement du launcher du frontend, notamment pour le développement local.
 *
 * Propriétés disponibles :
 *  - autoLaunch : active le lancement automatique du frontend au démarrage du backend.
 *  - jarPath : chemin vers le jar frontend compilé (laisser vide pour résolution automatique).
 *  - backendUrl : URL du backend passée au frontend (défaut http://localhost:8080).
 *  - autoStartSimulation : si true, démarre une nouvelle simulation et passe son id au frontend.
 *  - simulationId : id fixe de la simulation à visualiser (ignoré si autoStartSimulation=true).
 *  - jvmArgs : arguments JVM supplémentaires passés au process frontend (défaut --enable-preview).
 */
@ConfigurationProperties(prefix = "pixellife.frontend")
public record FrontendLauncherProperties(

        /** Active le lancement automatique du frontend. Défaut : false */
        @DefaultValue("false") boolean autoLaunch,

        /**
         * Chemin vers le jar frontend compilé.
         * Laisser vide pour la résolution automatique.
         * Défaut : vide
         */
        @DefaultValue("") String jarPath,

        /** URL du backend passée au frontend. Défaut : http://localhost:8080 */
        @DefaultValue("http://localhost:8080") String backendUrl,

        /**
         * Si true : démarre une nouvelle simulation et passe son id au frontend.
         * Si false : utilise simulationId.
         * Défaut : true
         */
        @DefaultValue("true") boolean autoStartSimulation,

        /**
         * Id fixe de la simulation à visualiser (ignoré si autoStartSimulation=true).
         * 0 = utilise la dernière simulation en base.
         * Défaut : 0
         */
        @DefaultValue("0") long simulationId,

        /**
         * Arguments JVM supplémentaires passés au process frontend.
         * Défaut : --enable-preview
         */
        @DefaultValue("--enable-preview") String jvmArgs

) {}