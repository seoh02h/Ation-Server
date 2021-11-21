package com.eureka.sensationserver.repository.persona;

import com.eureka.sensationserver.domain.persona.PersonaCharm;
import com.eureka.sensationserver.domain.persona.PersonaInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonaInterestRepository extends JpaRepository<PersonaInterest, Long> {
    List<PersonaInterest> findByPersona_Id(@Param(value="personaId")Long personaId);
    List<PersonaInterest> deleteByPersona_Id(@Param(value="personaId")Long personaId);


}
