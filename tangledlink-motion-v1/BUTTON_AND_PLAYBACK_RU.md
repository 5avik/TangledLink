# Кнопка и воспроизведение

## ConnectionPortal

Компонент использует Canvas: радиальный мягкий glow, тёмный центр, внешнее кольцо, вторую дугу и power/check/error-символ. Размер по умолчанию 224 dp; адаптировать к текущему экрану, сохраняя touch target минимум 48 dp. Палитры: Original — зелёный, Sakura — фиолетовый/розовый, Aurora — бирюзовый/синий, Cyberpunk — розовый/голубой.

Компонент имеет одну доступную clickable-область с Role.Button, contentDescription из actionLabel и stateDescription из statusLabel. Canvas-символы декоративны. Действие и названия предоставляет host; ошибка визуального состояния не должна подменять фактическое действие остановки/старта. Статус под кнопкой можно рендерить отдельным Text, избегая дублирования озвучивания.

Пример назначения параметров при интеграции:

```kotlin
ConnectionPortal(
    world = currentMotionWorld,
    state = actualVisualState,
    actionLabel = localizedCurrentAction,
    statusLabel = localizedActualStatus,
    enabled = canInvokeCurrentVpnAction,
    isAnimationEnabled = allowMotion,
    onClick = { onAction(MainAction.ToggleService) },
)
```

Имена текущего контракта перепроверить в проекте. Код компонента не знает MainAction и не вызывает VPN. Не ставить Connected в onClick или по завершении анимации. Не добавлять фиктивное Connecting, если источник состояния его не предоставляет. isRunning в прежнем проекте описывает запущенный сервис; не обещать доступность интернета по одному этому флагу.

Состояния:

- DISCONNECTED: приглушённый power и кольцо, без непрерывной анимации.
- CONNECTING: вращение дуг с периодом около 2 секунд; только при настоящем Connecting.
- CONNECTED: замкнутый контур, check, дыхание акцента около 4 секунд. Только из подтверждённого состояния приложения.
- ERROR: статичный мягкий error-акцент и восклицательный знак; текст ошибки из текущего UI-состояния.

intensity меняется за 300 ms при разрешённых анимациях; при отключении — snap. Кадровый цикл кнопки запускается только для moving-состояний и разрешённого движения. Callback анимации не меняет VPN и не отправляет действий.

## MP4-фон

Основной путь: локальный файл res/raw, один плеер на видимый главный экран, без аудио, controls, fullscreen и медиа-уведомления. Использовать существующий video-стек проекта; если его нет, агент может подключить актуальные совместимые Media3-модули. Не добавлять MediaSession/foreground service или audio focus ради декораций.

Для Media3: повтор одного элемента, volume = 0, скрытые controls, центрированное заполнение с обрезкой. До первого отрисованного кадра оставлять статичный постер; при decoder/player error показывать его же, без блокировки VPN-экрана. Не создавать/prepare плеер при каждой recomposition. На смене темы освобождать предыдущий decoder; не держать четыре плеера одновременно. При уходе со страницы или lifecycle ниже RESUMED playback останавливается; при dispose плеер освобождается. Уважать системную настройку уменьшения/отключения анимаций и ручную Reduce effects.

Видео-слой находится под Compose UI и не перехватывает touch/focus. Поверх него допустимы локальные scrim и непрозрачные поверхности текста. При использовании TextureView обеспечить правильный transform для Crop; при другом виде video surface проверить z-order, clip и слои Compose. Поведение loop и отсутствие чёрного кадра проверяются на устройстве.

Официальные ориентиры: [Media3 getting started](https://developer.android.com/media/media3/exoplayer/hello-world), [форматы Android](https://developer.android.com/media/platform/supported-formats). Версии библиотек в этом пакете намеренно не задаются: их выбирают по фактическому проекту.

## Canvas-фон

Альтернатива MP4: NativeMotionBackground принимает Painter исходного статичного фона, MotionWorld и isAnimationEnabled. Загружать Painter существующим асинхронным image loader, не декодировать картинки в кадре. Компонент рисует небольшое фиксированное число частиц и light-слоёв; верхний лимит обновления фона — 24 fps, портала — 60 fps. На конкретном refresh rate фактическая частота может быть ниже лимита.

Пример:

```kotlin
NativeMotionBackground(
    painter = asynchronouslyLoadedPoster,
    world = currentMotionWorld,
    isAnimationEnabled = allowMotion,
    modifier = Modifier.fillMaxSize(),
)
```

В выключенном режиме показывает статичный фон без наложений и camera drift. Картинка декодируется один раз существующим loader. Вариант имеет тот же характер эффектов, что MP4, но рендерится другим движком; точное совпадение кадров не обещается. Подходы Canvas/DrawScope описаны в [официальной документации Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview).

## Политика движения

MotionMath.allowed принимает isResumed, isVisible, reduceMotion, systemScale и powerSave. Это чистая функция; host наблюдает lifecycle/видимость и актуальные системные настройки. Settings.Global.ANIMATOR_DURATION_SCALE только читать, не менять; обновлять при изменении через подходящий observer. PowerManager.isPowerSaveMode — сигнал для статичного режима. Никакие desktop-конфиги и системные настройки пользователя не менять.

Параметр isAnimationEnabled нельзя безусловно передавать true. Отключить движение и декодирование в скрытом экране, при системном масштабе 0, ручном reduce motion и экономии энергии. Для портала, находящегося внутри видимого экрана, можно отдельно проверить видимость компонента. Сам VPN продолжает работать по своей обычной архитектуре.

Производительность, память, первое появление, loop, расход батареи и корректность layer/focus требуют проверки на устройстве; компиляция этих файлов их не доказывает.
