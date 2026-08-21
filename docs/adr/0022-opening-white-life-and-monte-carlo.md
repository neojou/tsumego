# 開局白活點只算一次；蒙地卡羅只猜順序再精確驗證

「黑停時白哪裡可以做活」只在開局算一次，得到開局白活點；之後每條搜尋路徑先驗證白下該點，不是等黑下到遠處才探。蒙地卡羅用來猜一個最佳落點當著手順序，不取代「黑能否強迫」的精確證明，也不改最長抵抗。成功或失敗後可重做，搜尋路徑保留。

搜尋加速改走 ADR-0023 的相關區（RZS）。開局白活點與蒙地卡羅不再接入解題搜尋。重做仍有效。

Supersedes the tenuki-only probe in ADR-0021. Directory memory in ADR-0021 still stands. Search-acceleration parts superseded by ADR-0023.
