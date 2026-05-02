package com.moba.battle.battle;

import lombok.extern.slf4j.Slf4j;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.collision.Manifold;
import org.jbox2d.dynamics.contacts.Contact;
import org.jbox2d.common.Vec2;

import java.util.List;

@Slf4j
public class CollisionListener implements ContactListener {

    private final PhysicsBodyMapper bodyMapper;
    private final List<PhysicsWorld.CollisionEvent> collisionEvents;

    public CollisionListener(PhysicsBodyMapper bodyMapper, List<PhysicsWorld.CollisionEvent> collisionEvents) {
        this.bodyMapper = bodyMapper;
        this.collisionEvents = collisionEvents;
    }

    @Override
    public void beginContact(Contact contact) {
        long bodyIdA = bodyMapper.getBodyId(contact.getFixtureA().getBody());
        long bodyIdB = bodyMapper.getBodyId(contact.getFixtureB().getBody());

        if (bodyIdA < 0 || bodyIdB < 0) return;

        PhysicsWorld.CollisionEvent event = new PhysicsWorld.CollisionEvent();
        event.setBodyIdA(bodyIdA);
        event.setBodyIdB(bodyIdB);
        event.setGameObjectIdA(bodyMapper.getGameObjectId(bodyIdA));
        event.setGameObjectIdB(bodyMapper.getGameObjectId(bodyIdB));
        event.setTypeA(bodyMapper.getBodyType(bodyIdA));
        event.setTypeB(bodyMapper.getBodyType(bodyIdB));
        event.setSensor(contact.getFixtureA().isSensor() || contact.getFixtureB().isSensor());

        collisionEvents.add(event);
    }

    @Override
    public void endContact(Contact contact) {
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }
}
