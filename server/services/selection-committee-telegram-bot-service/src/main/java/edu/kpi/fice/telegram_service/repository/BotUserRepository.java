package edu.kpi.fice.telegram_service.repository;

import edu.kpi.fice.telegram_service.domain.BotUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {

  List<BotUser> findAllBySubscribedTrue();
}
