    private void doBoost(EventMove e) {
        if (mc.player.getInventory().getStack(38).getItem() != Items.ELYTRA) {
            return;
        }
        
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            mc.player.setVelocity(mc.player.getVelocity().x, 1.2, mc.player.getVelocity().z);
            mc.player.jump();
            sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            return;
        }
        
        if (!mc.player.isFallFlying() || mc.player.isTouchingWater() || mc.player.isInLava()) {
            return;
        }
        
        if (noSpeedLoss.getValue()) {
            double currentSpeed = Math.hypot(e.getX(), e.getZ());
            if (currentSpeed < 0.1) {
                double[] dir = MovementUtility.forward(0.5);
                e.setX(e.getX() + dir[0]);
                e.setZ(e.getZ() + dir[1]);
            }
        }
        
        if (autoClimb.getValue() && mc.options.jumpKey.isPressed()) {
            e.setY(e.getY() + 0.3);
        }
        
        if (burstMode.getValue() && mc.player.age % 10 == 0) {
            double[] dir = MovementUtility.forward(burstStrength.getValue() / 5f);
            e.setX(e.getX() + dir[0]);
            e.setZ(e.getZ() + dir[1]);
        }
        
        if (glideBoost.getValue()) {
            e.setY(e.getY() - 0.05);
            double[] dir = MovementUtility.forward(glideFactor.getValue() / 8f);
            e.setX(e.getX() + dir[0]);
            e.setZ(e.getZ() + dir[1]);
        }
        
        if (waveMode.getValue()) {
            double wave = Math.sin(mc.player.age * 0.1) * waveAmplitude.getValue();
            e.setY(e.getY() + wave * 0.1);
        }
        
        int currentPing = getCurrentPing();
        if (currentPing == 0) currentPing = 140;
        
        long minDelay = Math.min(300, Math.max(60, (long)(currentPing * 0.8)));
        
        if (!boostCooldownTimer.passedMs(minDelay)) {
            return;
        }
        
        if (twoBee.getValue()) {
            if ((mc.options.jumpKey.isPressed() || !onlySpace.getValue() || cruiseControl.getValue())) {
                double[] m = MovementUtility.forwardWithoutStrafe((factor.getValue() / 10f));
                e.setX(e.getX() + m[0]);
                e.setZ(e.getZ() + m[1]);
            }
        }
        
        double speed = Math.hypot(e.getX(), e.getZ());
        if (speedLimit.getValue() && speed > maxSpeed.getValue()) {
            e.setX(e.getX() * maxSpeed.getValue() / speed);
            e.setZ(e.getZ() * maxSpeed.getValue() / speed);
        }
        
        mc.player.setVelocity(e.getX(), e.getY(), e.getZ());
        e.cancel();
        
        boostCooldownTimer.reset();
    }
